package com.swadeshiscanner.app.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.itextpdf.text.Document
import com.itextpdf.text.Image
import com.itextpdf.text.PageSize
import com.itextpdf.text.Rectangle
import com.itextpdf.text.pdf.PdfWriter
import com.swadeshiscanner.app.database.PageEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

object ExportUtils {

    // --- CONFIGURATION ---

    // Target Sizes (Strict Limits)
    // Low Res is now the old High Res (100KB)
    // High Res is now strictly under 200KB
    private const val SIZE_TARGET_LOW = 100 * 1024   // 100 KB
    private const val SIZE_TARGET_HIGH = 200 * 1024  // 200 KB

    // Resolution Ceilings (Starting Points)
    // Low starts at 2200 (Old High)
    // High starts at 2600 (Better detail for the 200KB allowance)
    private const val RES_LOW_START = 2200
    private const val RES_HIGH_START = 2600

    // --- DETAIL FILTER (UNCHANGED) ---
    private const val CONTRAST = 1.1f
    private const val BRIGHTNESS = -10f
    private const val SATURATION = 0.7f

    enum class MimeType(val mime: String, val ext: String, val directory: String) {
        IMAGE("image/jpeg", "jpg", Environment.DIRECTORY_PICTURES),
        PDF("application/pdf", "pdf", Environment.DIRECTORY_DOCUMENTS),
        WORD("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx", Environment.DIRECTORY_DOCUMENTS),
    }

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OCR Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    // --------------------------------------------------------------------------------
    //  PDF GENERATION
    // --------------------------------------------------------------------------------

    fun generatePdf(context: Context, pages: List<PageEntity>, docName: String): File {
        return createOptimizedPdf(context, pages, docName, null, forceLandscape = false)
    }

    fun generatePpt(context: Context, pages: List<PageEntity>, docName: String): File {
        return createOptimizedPdf(context, pages, docName, null, forceLandscape = true)
    }

    fun generateProtectedPdf(context: Context, pages: List<PageEntity>, docName: String, password: String): File? {
        val file = createOptimizedPdf(context, pages, docName, password, forceLandscape = false)
        return if (file.exists() && file.length() > 0) file else null
    }

    private fun createOptimizedPdf(
        context: Context,
        pages: List<PageEntity>,
        docName: String,
        password: String?,
        forceLandscape: Boolean
    ): File {
        val safeName = docName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val ext = if (forceLandscape) "_slides.pdf" else ".pdf"
        val file = File(context.cacheDir, "$safeName$ext")

        if (pages.isEmpty()) return file

        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isHighQuality = prefs.getBoolean("high_quality", true)

        val targetRes = if (isHighQuality) RES_HIGH_START else RES_LOW_START
        val targetSize = if (isHighQuality) SIZE_TARGET_HIGH else SIZE_TARGET_LOW

        val document = Document(PageSize.A4, 0f, 0f, 0f, 0f)

        try {
            val fileOut = FileOutputStream(file)
            val writer = PdfWriter.getInstance(document, fileOut)

            if (!password.isNullOrEmpty()) {
                writer.setEncryption(
                    password.toByteArray(),
                    password.toByteArray(),
                    PdfWriter.ALLOW_PRINTING or PdfWriter.ALLOW_COPY,
                    PdfWriter.ENCRYPTION_AES_128
                )
            }

            document.open()

            for (page in pages) {
                val path = page.processedPath ?: page.originalPath
                if (!File(path).exists()) continue

                // 1. FILTER & INITIAL SCALE
                var processedBitmap = processBitmapForPdf(path, targetRes) ?: continue

                // 2. SMART COMPRESSION
                val imageBytes = compressToTargetSize(processedBitmap, targetSize)

                // 3. iText Insertion
                val itextImage = Image.getInstance(imageBytes)

                if (forceLandscape) {
                    val slideWidth = 842f
                    val slideHeight = 474f
                    document.setPageSize(Rectangle(slideWidth, slideHeight))
                    document.newPage()
                    itextImage.scaleToFit(slideWidth, slideHeight)
                    val x = (slideWidth - itextImage.scaledWidth) / 2f
                    val y = (slideHeight - itextImage.scaledHeight) / 2f
                    itextImage.setAbsolutePosition(x, y)
                } else {
                    val pdfWidth = PageSize.A4.width // 595
                    val scale = pdfWidth / itextImage.width
                    val pdfHeight = itextImage.height * scale
                    document.setPageSize(Rectangle(pdfWidth, pdfHeight))
                    document.newPage()
                    itextImage.scaleToFit(pdfWidth, pdfHeight)
                    itextImage.setAbsolutePosition(0f, 0f)
                }

                document.add(itextImage)

                if (!processedBitmap.isRecycled) processedBitmap.recycle()
            }
            document.close()
        } catch (e: Exception) {
            e.printStackTrace()
            try { if (document.isOpen) document.close() } catch (_: Exception) {}
        }
        return file
    }

    /**
     * THE FIX: "See-Saw" Compression Algorithm
     */
    private fun compressToTargetSize(sourceBitmap: Bitmap, maxBytes: Int): ByteArray {
        var currentBitmap = sourceBitmap
        var quality = 85 // Start high
        val stream = ByteArrayOutputStream()

        var attempts = 0
        while (attempts < 10) {
            stream.reset()
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)

            val size = stream.size()

            if (size <= maxBytes) {
                return stream.toByteArray()
            }

            if (quality > 50) {
                quality -= 10
            } else {
                // Resize logic
                val newW = (currentBitmap.width * 0.85).toInt()
                val newH = (currentBitmap.height * 0.85).toInt()

                if (newW < 600) break

                val scaledBitmap = Bitmap.createScaledBitmap(currentBitmap, newW, newH, true)

                if (currentBitmap != sourceBitmap) {
                    currentBitmap.recycle()
                }
                currentBitmap = scaledBitmap
                quality = 80
            }
            attempts++
        }
        return stream.toByteArray()
    }

    private fun processBitmapForPdf(path: String, targetMaxDim: Int): Bitmap? {
        try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)

            var inSampleSize = 1
            if (options.outHeight > targetMaxDim || options.outWidth > targetMaxDim) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= targetMaxDim && (halfWidth / inSampleSize) >= targetMaxDim) {
                    inSampleSize *= 2
                }
            }

            options.inSampleSize = inSampleSize
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val original = BitmapFactory.decodeFile(path, options) ?: return null

            val ratio = targetMaxDim.toFloat() / max(original.width, original.height)
            val newW = (original.width * ratio).toInt()
            val newH = (original.height * ratio).toInt()

            val result = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)

            val paint = Paint()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true

            // --- FILTER: PRESERVE FAINT LINES ---
            val cm = ColorMatrix()
            cm.setSaturation(SATURATION)

            val c = CONTRAST
            val b = BRIGHTNESS

            val contrastMatrix = ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, b,
                0f, c, 0f, 0f, b,
                0f, 0f, c, 0f, b,
                0f, 0f, 0f, 1f, 0f
            ))

            cm.postConcat(contrastMatrix)
            paint.colorFilter = ColorMatrixColorFilter(cm)

            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(original, null, android.graphics.Rect(0, 0, newW, newH), paint)

            original.recycle()
            return result

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // --------------------------------------------------------------------------------
    //  WORD & TEXT GENERATION (Standard)
    // --------------------------------------------------------------------------------

    fun generateWordFromImages(context: Context, pages: List<PageEntity>, docName: String): File? {
        if (pages.isEmpty()) return null
        val safeName = docName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val file = File(context.cacheDir, "$safeName.docx")

        try {
            val fos = FileOutputStream(file)
            val zip = ZipOutputStream(fos)
            writeToZip(zip, "[Content_Types].xml", getContentTypesXml())
            writeToZip(zip, "_rels/.rels", getRelsXml())

            val relsBuilder = StringBuilder()
            relsBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")

            val docBuilder = StringBuilder()
            docBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><w:body>")
            docBuilder.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"36\"/></w:rPr><w:t>$docName</w:t></w:r></w:p>")

            var rIdCounter = 1
            for (page in pages) {
                val path = page.processedPath ?: page.originalPath
                if (!File(path).exists()) continue
                val bitmap = decodeSampledBitmap(path, 1000) ?: continue
                val imgFileName = "image$rIdCounter.jpeg"
                val entry = ZipEntry("word/media/$imgFileName")
                zip.putNextEntry(entry)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, zip)
                zip.closeEntry()

                val rId = "rId$rIdCounter"
                relsBuilder.append("<Relationship Id=\"$rId\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/$imgFileName\"/>")

                val widthEmus = (bitmap.width * 9525).toLong()
                val heightEmus = (bitmap.height * 9525).toLong()
                val maxEmu = 5500000L
                val finalW: Long
                val finalH: Long
                if (widthEmus > maxEmu) {
                    val ratio = maxEmu.toDouble() / widthEmus
                    finalW = maxEmu
                    finalH = (heightEmus * ratio).toLong()
                } else {
                    finalW = widthEmus
                    finalH = heightEmus
                }

                docBuilder.append(getImageXml(rId, rIdCounter, finalW, finalH))
                docBuilder.append("<w:p/>")
                bitmap.recycle()
                rIdCounter++
            }
            docBuilder.append("</w:body></w:document>")
            relsBuilder.append("</Relationships>")
            writeToZip(zip, "word/document.xml", docBuilder.toString())
            writeToZip(zip, "word/_rels/document.xml.rels", relsBuilder.toString())
            zip.close()
            return file
        } catch (e: Exception) { return null }
    }

    fun generateWordFromText(context: Context, text: String): File? {
        if (text.isEmpty()) return null
        val file = File(context.cacheDir, "OCR_${System.currentTimeMillis()}.docx")
        try {
            val fos = FileOutputStream(file)
            val zip = ZipOutputStream(fos)
            writeToZip(zip, "[Content_Types].xml", getContentTypesXml())
            writeToZip(zip, "_rels/.rels", getRelsXml())
            val emptyRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"></Relationships>"
            writeToZip(zip, "word/_rels/document.xml.rels", emptyRels)

            val docBuilder = StringBuilder()
            docBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>")
            docBuilder.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"32\"/></w:rPr><w:t>OCR Result</w:t></w:r></w:p>")
            text.split("\n").forEach { line ->
                val safeLine = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                docBuilder.append("<w:p><w:r><w:t>$safeLine</w:t></w:r></w:p>")
            }
            docBuilder.append("</w:body></w:document>")
            writeToZip(zip, "word/document.xml", docBuilder.toString())
            zip.close()
            return file
        } catch (e: Exception) { return null }
    }

    fun exportTextToPdf(context: Context, text: String) {
        val pdfDocument = PdfDocument()
        val pageHeight = 842
        val pageWidth = 595
        val margin = 40
        val contentWidth = pageWidth - (2 * margin)
        val textPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 12f
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, contentWidth)
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f)
            .setIncludePad(false).build()

        val contentHeightPerPage = pageHeight - (2 * margin)
        var totalHeightDrawn = 0
        val totalTextHeight = staticLayout.height
        var pageIndex = 1

        while (totalHeightDrawn < totalTextHeight) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            canvas.save()
            canvas.translate(margin.toFloat(), margin.toFloat())
            canvas.translate(0f, (-totalHeightDrawn).toFloat())
            canvas.clipRect(0, totalHeightDrawn, contentWidth, totalHeightDrawn + contentHeightPerPage)
            staticLayout.draw(canvas)
            canvas.restore()
            pdfDocument.finishPage(page)
            totalHeightDrawn += contentHeightPerPage
            pageIndex++
        }

        val file = File(context.cacheDir, "OCR_Text_${System.currentTimeMillis()}.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            shareFile(context, file, MimeType.PDF.mime)
        } catch (e: IOException) { pdfDocument.close() }
    }

    // --------------------------------------------------------------------------------
    //  UTILITIES
    // --------------------------------------------------------------------------------

    fun saveToDevice(context: Context, sourceFile: File, type: MimeType, finalName: String): Boolean {
        if (!sourceFile.exists()) return false
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$finalName.${type.ext}")
            put(MediaStore.MediaColumns.MIME_TYPE, type.mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, type.directory + "/SwadeshiScanner")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        try {
            val collection = if (type == MimeType.IMAGE) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Files.getContentUri("external")
            }
            val itemUri = resolver.insert(collection, contentValues) ?: return false
            resolver.openOutputStream(itemUri).use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out!!) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }
            return true
        } catch (e: Exception) { return false }
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Document")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun shareMultipleFiles(context: Context, files: List<File>, mimeType: String) {
        val uris = ArrayList<Uri>()
        files.forEach { uris.add(FileProvider.getUriForFile(context, "${context.packageName}.provider", it)) }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Files")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun getContentTypesXml() = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>"
    private fun getRelsXml() = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>"
    private fun getImageXml(rId: String, id: Int, cx: Long, cy: Long) = "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"$cx\" cy=\"$cy\"/><wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/><wp:docPr id=\"$id\" name=\"Image $id\"/><wp:cNvGraphicFramePr><a:graphicFrameLocks xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" noChangeAspect=\"1\"/></wp:cNvGraphicFramePr><a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:nvPicPr><pic:cNvPr id=\"$id\" name=\"Image $id\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"$rId\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"
    private fun writeToZip(zip: ZipOutputStream, entryName: String, content: String) { zip.putNextEntry(ZipEntry(entryName)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry() }

    private fun decodeSampledBitmap(path: String, targetMaxDim: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        var inSampleSize = 1
        while ((options.outHeight / 2 / inSampleSize) >= targetMaxDim && (options.outWidth / 2 / inSampleSize) >= targetMaxDim) inSampleSize *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = inSampleSize })
    }
}