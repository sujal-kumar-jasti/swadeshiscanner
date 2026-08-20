# Swadeshi Scanner

Swadeshi Scanner is a professional Android application for high-precision document scanning and intelligent processing. It combines advanced computer vision with artificial intelligence to provide a comprehensive suite of tools for digitizing and converting documents.

## Core Scanning and Processing

### Precision Capture
*   **Smart Detection**: Real-time perspective detection using CameraX to identify document boundaries instantly.
*   **Perspective Correction**: Advanced warping algorithms to straighten tilted scans and remove background noise.
*   **Enhancement Filters**: High-performance image processing including Magic Color, B&W, and Grayscale modes for maximum legibility.

### Document Management
*   **Database Persistence**: Full local storage using Room for offline access and document integrity.
*   **Intelligent Search**: Real-time document indexing with a signature "Glow" search bar for quick retrieval.
*   **Batch Operations**: Ability to reorder, delete, and apply filters to multiple pages simultaneously.

## Intelligence and Extraction

### Text and Math Intelligence
*   **Multilingual OCR**: High-accuracy text extraction powered by the Tesseract engine.
*   **Formula Solver**: Specialized scanning for mathematical equations with an integrated solver and LaTeX renderer.
*   **Real-time Translation**: Instant document translation using Google ML Kit models with support for offline language packs.

### Specialized Workflows
*   **ID Card Mode**: Sequential scanning of both sides of an identification card with automatic merging onto a single page.
*   **Passport Maker**: Automated cropping and grid generation for standard-sized passport photos.
*   **Book Splitting**: Captures landscape book spreads and automatically splits them into individual, portrait-aligned pages.
*   **Electronic Signatures**: Create and manage a library of signatures that can be scaled, rotated, and burned into documents.

## Backend and Conversion

Swadeshi Scanner is integrated with a high-performance distributed backend for complex file format transformations:

*   **Conversion Engine (FastAPI)**: A dedicated Python-based server that handles heavy processing for Microsoft Office formats.
*   **Export Formats**: Seamlessly convert PDF documents into editable Word (.docx), Excel (.xlsx), and PowerPoint (.pptx) files.
*   **Import Formats**: High-fidelity conversion from Word, Excel, and PPT files back into PDF using LibreOffice headless processing.

## Technical Architecture

*   **UI Framework**: 100% Jetpack Compose with a modern Material 3 design system.
*   **Concurrency**: Kotlin Coroutines and StateFlow for efficient, non-blocking image processing.
*   **Camera API**: CameraX for hardware-accelerated image capture and lifecycle management.
*   **Networking**: Retrofit and OkHttp for secure communication with the conversion backend.
*   **Theming**: Dynamic Light and Dark mode support with optimized OLED backgrounds.

## Author

J Sujal Kumar
*   GitHub: [sujal-kumar-jasti](https://github.com/sujal-kumar-jasti)
*   LinkedIn: [sujalkumarjasti](https://www.linkedin.com/in/sujalkumarjasti)
*   Email: sujalkumarjasti751@gmail.com
