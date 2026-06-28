#  BioSequenceAnalyzer

A full-stack bioinformatics desktop application for analyzing DNA, RNA, and Protein sequences.

**JavaFX frontend + Python Flask backend**

## Features
- DNA analysis: GC content, AT content, complement, reverse complement, melting temperature, ORF detection, transcription
- RNA analysis: translation to protein, codon table, base composition, back-transcription to DNA
- Protein analysis: molecular weight, isoelectric point, hydrophobicity, instability index, secondary structure prediction, amino acid composition
- Mutation detection: substitution, insertion, deletion identification between two sequences
- Interactive bar chart and pie chart visualization
- CSV export of all results
- Sample sequences for quick testing

## Tech Stack
- Frontend: Java 17 + JavaFX 21 (FXML, CSS, Charts, TableView)
- Backend: Python 3.10+ Flask REST API
- Communication: Apache HttpClient5 + Jackson JSON
- Build: Maven

## How to Run

**Step 1 - Start backend:**
```
cd backend
pip install flask flask-cors
python app.py
```

**Step 2 - Start frontend (new terminal):**
```
cd frontend
mvn javafx:run
```

## Author
Rania Lal - BS Bioinformatics, IIUI 2025
