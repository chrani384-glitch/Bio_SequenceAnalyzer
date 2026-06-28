"""
BioSequenceAnalyzer - Backend API Server
=========================================
Complete bioinformatics analysis engine.
Handles DNA, RNA, and Protein sequences.

Run this first before starting the JavaFX app:
    pip install flask flask-cors
    python app.py
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
import re
import math
from collections import Counter

app = Flask(__name__)
CORS(app)

# ─────────────────────────────────────────────────────────────
# CODON TABLE — converts 3-letter RNA codons to amino acids
# This is the universal genetic code used by almost all life
# ─────────────────────────────────────────────────────────────
CODON_TABLE = {
    'UUU': 'Phe', 'UUC': 'Phe', 'UUA': 'Leu', 'UUG': 'Leu',
    'CUU': 'Leu', 'CUC': 'Leu', 'CUA': 'Leu', 'CUG': 'Leu',
    'AUU': 'Ile', 'AUC': 'Ile', 'AUA': 'Ile', 'AUG': 'Met',
    'GUU': 'Val', 'GUC': 'Val', 'GUA': 'Val', 'GUG': 'Val',
    'UCU': 'Ser', 'UCC': 'Ser', 'UCA': 'Ser', 'UCG': 'Ser',
    'CCU': 'Pro', 'CCC': 'Pro', 'CCA': 'Pro', 'CCG': 'Pro',
    'ACU': 'Thr', 'ACC': 'Thr', 'ACA': 'Thr', 'ACG': 'Thr',
    'GCU': 'Ala', 'GCC': 'Ala', 'GCA': 'Ala', 'GCG': 'Ala',
    'UAU': 'Tyr', 'UAC': 'Tyr', 'UAA': 'Stop','UAG': 'Stop',
    'CAU': 'His', 'CAC': 'His', 'CAA': 'Gln', 'CAG': 'Gln',
    'AAU': 'Asn', 'AAC': 'Asn', 'AAA': 'Lys', 'AAG': 'Lys',
    'GAU': 'Asp', 'GAC': 'Asp', 'GAA': 'Glu', 'GAG': 'Glu',
    'UGU': 'Cys', 'UGC': 'Cys', 'UGA': 'Stop','UGG': 'Trp',
    'CGU': 'Arg', 'CGC': 'Arg', 'CGA': 'Arg', 'CGG': 'Arg',
    'AGU': 'Ser', 'AGC': 'Ser', 'AGA': 'Arg', 'AGG': 'Arg',
    'GGU': 'Gly', 'GGC': 'Gly', 'GGA': 'Gly', 'GGG': 'Gly',
}

# Amino acid molecular weights (Da)
AA_WEIGHTS = {
    'A': 89.09,  'R': 174.20, 'N': 132.12, 'D': 133.10,
    'C': 121.16, 'E': 147.13, 'Q': 146.15, 'G': 75.03,
    'H': 155.16, 'I': 131.17, 'L': 131.17, 'K': 146.19,
    'M': 149.21, 'F': 165.19, 'P': 115.13, 'S': 105.09,
    'T': 119.12, 'W': 204.23, 'Y': 181.19, 'V': 117.15,
}

# Amino acid full names
AA_NAMES = {
    'A': 'Alanine',    'R': 'Arginine',   'N': 'Asparagine', 'D': 'Aspartate',
    'C': 'Cysteine',   'E': 'Glutamate',  'Q': 'Glutamine',  'G': 'Glycine',
    'H': 'Histidine',  'I': 'Isoleucine', 'L': 'Leucine',    'K': 'Lysine',
    'M': 'Methionine', 'F': 'Phenylalanine','P': 'Proline',  'S': 'Serine',
    'T': 'Threonine',  'W': 'Tryptophan', 'Y': 'Tyrosine',   'V': 'Valine',
}

# ─────────────────────────────────────────────────────────────
# HELPER: Detect sequence type
# ─────────────────────────────────────────────────────────────
def detect_sequence_type(seq):
    seq = seq.upper().strip()
    dna_bases  = set('ATGCN')
    rna_bases  = set('AUGCN')
    prot_bases = set('ACDEFGHIKLMNPQRSTVWY')

    unique = set(seq)

    if unique <= dna_bases and 'T' in unique and 'U' not in unique:
        return 'DNA'
    elif unique <= rna_bases and 'U' in unique:
        return 'RNA'
    elif unique <= prot_bases:
        return 'PROTEIN'
    elif unique <= dna_bases:
        return 'DNA'
    else:
        return 'UNKNOWN'

# ─────────────────────────────────────────────────────────────
# DNA ANALYSIS
# ─────────────────────────────────────────────────────────────
def analyze_dna(seq):
    seq = seq.upper().replace(' ', '').replace('\n', '')
    length = len(seq)

    # Base counts
    counts = Counter(seq)
    a = counts.get('A', 0)
    t = counts.get('T', 0)
    g = counts.get('G', 0)
    c = counts.get('C', 0)

    # GC content
    gc_content = round((g + c) / length * 100, 2) if length > 0 else 0
    at_content = round((a + t) / length * 100, 2) if length > 0 else 0

    # Complement
    complement_map = str.maketrans('ATGCN', 'TACGN')
    complement = seq.translate(complement_map)
    reverse_complement = complement[::-1]

    # Transcription DNA -> RNA
    rna = seq.replace('T', 'U')

    # Melting temperature (Wallace rule for short, nearest-neighbor approximation)
    if length < 14:
        tm = round(2 * (a + t) + 4 * (g + c), 2)
    else:
        tm = round(64.9 + 41 * (g + c - 16.4) / length, 2)

    # Find ORFs (Open Reading Frames)
    orfs = find_orfs(seq)

    # Codon usage from first ORF
    codon_usage = {}
    if orfs:
        codon_usage = calculate_codon_usage(seq[orfs[0]['start']:orfs[0]['end']])

    # Nucleotide frequencies for chart
    nucleotide_freq = {
        'A': round(a / length * 100, 2) if length > 0 else 0,
        'T': round(t / length * 100, 2) if length > 0 else 0,
        'G': round(g / length * 100, 2) if length > 0 else 0,
        'C': round(c / length * 100, 2) if length > 0 else 0,
    }

    # Molecular weight (average for dsDNA)
    mol_weight = round(length * 607.4 + 157.9, 2)

    return {
        'type': 'DNA',
        'length': length,
        'sequence': seq,
        'base_counts': {'A': a, 'T': t, 'G': g, 'C': c},
        'gc_content': gc_content,
        'at_content': at_content,
        'nucleotide_freq': nucleotide_freq,
        'complement': complement,
        'reverse_complement': reverse_complement,
        'rna_transcript': rna,
        'melting_temp': tm,
        'molecular_weight': mol_weight,
        'orfs': orfs[:10],  # top 10 ORFs
        'orf_count': len(orfs),
        'codon_usage': codon_usage,
        'is_palindrome': seq == reverse_complement,
    }

# ─────────────────────────────────────────────────────────────
# ORF FINDER
# ─────────────────────────────────────────────────────────────
def find_orfs(seq, min_length=30):
    """Find all Open Reading Frames in all 6 reading frames."""
    orfs = []
    seq = seq.upper()
    rc = seq.translate(str.maketrans('ATGC', 'TACG'))[::-1]

    for strand, dna in [(+1, seq), (-1, rc)]:
        for frame in range(3):
            i = frame
            while i < len(dna) - 2:
                codon = dna[i:i+3]
                if codon == 'ATG':  # Start codon
                    start = i
                    for j in range(i, len(dna) - 2, 3):
                        stop_codon = dna[j:j+3]
                        if stop_codon in ['TAA', 'TAG', 'TGA']:
                            orf_len = j + 3 - start
                            if orf_len >= min_length:
                                protein = translate_dna(dna[start:j+3])
                                orfs.append({
                                    'start': start,
                                    'end': j + 3,
                                    'length': orf_len,
                                    'strand': strand,
                                    'frame': frame + 1,
                                    'protein': protein,
                                    'aa_length': len(protein),
                                })
                            i = j + 3
                            break
                    else:
                        i += 3
                        continue
                else:
                    i += 3

    orfs.sort(key=lambda x: x['length'], reverse=True)
    return orfs

# ─────────────────────────────────────────────────────────────
# TRANSLATION
# ─────────────────────────────────────────────────────────────
def translate_dna(dna_seq):
    """Translate DNA sequence to protein."""
    rna = dna_seq.upper().replace('T', 'U')
    protein = []
    for i in range(0, len(rna) - 2, 3):
        codon = rna[i:i+3]
        aa = CODON_TABLE.get(codon, '?')
        if aa == 'Stop':
            break
        protein.append(aa)
    return '-'.join(protein)

def translate_rna(rna_seq):
    """Translate RNA to protein with full details."""
    rna_seq = rna_seq.upper().strip()
    protein = []
    codons_used = []

    for i in range(0, len(rna_seq) - 2, 3):
        codon = rna_seq[i:i+3]
        if len(codon) < 3:
            break
        aa = CODON_TABLE.get(codon, '?')
        codons_used.append({'codon': codon, 'amino_acid': aa, 'position': i // 3 + 1})
        if aa == 'Stop':
            break
        protein.append(aa)

    return protein, codons_used

# ─────────────────────────────────────────────────────────────
# CODON USAGE
# ─────────────────────────────────────────────────────────────
def calculate_codon_usage(dna_seq):
    """Calculate codon usage frequency."""
    rna = dna_seq.upper().replace('T', 'U')
    codon_counts = Counter()
    for i in range(0, len(rna) - 2, 3):
        codon = rna[i:i+3]
        if len(codon) == 3:
            codon_counts[codon] += 1

    total = sum(codon_counts.values())
    return {
        codon: {
            'count': count,
            'frequency': round(count / total * 100, 2),
            'amino_acid': CODON_TABLE.get(codon, '?')
        }
        for codon, count in codon_counts.most_common(20)
    }

# ─────────────────────────────────────────────────────────────
# RNA ANALYSIS
# ─────────────────────────────────────────────────────────────
def analyze_rna(seq):
    seq = seq.upper().replace(' ', '').replace('\n', '').replace('T', 'U')
    length = len(seq)

    counts = Counter(seq)
    a = counts.get('A', 0)
    u = counts.get('U', 0)
    g = counts.get('G', 0)
    c = counts.get('C', 0)

    gc_content = round((g + c) / length * 100, 2) if length > 0 else 0

    # Translate RNA to protein
    protein_aas, codon_details = translate_rna(seq)
    protein_str = '-'.join(protein_aas)

    # Back-transcribe to DNA
    dna = seq.replace('U', 'T')

    nucleotide_freq = {
        'A': round(a / length * 100, 2) if length > 0 else 0,
        'U': round(u / length * 100, 2) if length > 0 else 0,
        'G': round(g / length * 100, 2) if length > 0 else 0,
        'C': round(c / length * 100, 2) if length > 0 else 0,
    }

    return {
        'type': 'RNA',
        'length': length,
        'sequence': seq,
        'base_counts': {'A': a, 'U': u, 'G': g, 'C': c},
        'gc_content': gc_content,
        'nucleotide_freq': nucleotide_freq,
        'dna_template': dna,
        'protein_translation': protein_str,
        'protein_length': len(protein_aas),
        'codon_details': codon_details[:30],
        'has_start_codon': 'AUG' in seq,
        'has_stop_codon': any(s in seq for s in ['UAA', 'UAG', 'UGA']),
    }

# ─────────────────────────────────────────────────────────────
# PROTEIN ANALYSIS
# ─────────────────────────────────────────────────────────────
def analyze_protein(seq):
    seq = seq.upper().replace(' ', '').replace('\n', '')
    length = len(seq)

    # Amino acid composition
    aa_counts = Counter(seq)

    # Molecular weight
    mol_weight = sum(AA_WEIGHTS.get(aa, 110) for aa in seq)
    mol_weight = round(mol_weight - (length - 1) * 18.02, 2)  # subtract water

    # Isoelectric point (simplified)
    positive_aa = seq.count('R') + seq.count('K') + seq.count('H')
    negative_aa = seq.count('D') + seq.count('E')
    pi = round(7.0 + 1.5 * (positive_aa - negative_aa) / max(length, 1), 2)
    pi = max(1.0, min(14.0, pi))

    # Hydrophobicity (Kyte-Doolittle)
    hydro_scale = {
        'A': 1.8,  'R': -4.5, 'N': -3.5, 'D': -3.5, 'C': 2.5,
        'Q': -3.5, 'E': -3.5, 'G': -0.4, 'H': -3.2, 'I': 4.5,
        'L': 3.8,  'K': -3.9, 'M': 1.9,  'F': 2.8,  'P': -1.6,
        'S': -0.8, 'T': -0.7, 'W': -0.9, 'Y': -1.3, 'V': 4.2,
    }
    hydro_score = round(
        sum(hydro_scale.get(aa, 0) for aa in seq) / max(length, 1), 3
    )

    # Instability index (simplified Guruprasad)
    instability_dipeptides = {
        'WW': 1.0, 'WC': 1.0, 'WR': 1.0, 'WK': 1.0,
        'EW': 1.0, 'DW': 1.0, 'NW': 1.0,
    }
    instability = sum(
        instability_dipeptides.get(seq[i:i+2], 0)
        for i in range(len(seq) - 1)
    )
    instability_index = round(instability * 10 / max(length, 1), 2)
    is_stable = instability_index < 40

    # Amino acid composition percentages for chart
    aa_composition = {}
    for aa, name in AA_NAMES.items():
        count = aa_counts.get(aa, 0)
        aa_composition[aa] = {
            'name': name,
            'count': count,
            'percentage': round(count / length * 100, 2) if length > 0 else 0,
            'weight': AA_WEIGHTS.get(aa, 0),
        }

    # Secondary structure prediction (simplified Chou-Fasman)
    helix_formers  = set('AELM')
    sheet_formers  = set('VIY')
    turn_formers   = set('GNPS')

    helix_count  = sum(1 for aa in seq if aa in helix_formers)
    sheet_count  = sum(1 for aa in seq if aa in sheet_formers)
    turn_count   = sum(1 for aa in seq if aa in turn_formers)
    coil_count   = length - helix_count - sheet_count - turn_count

    secondary_structure = {
        'helix':  round(helix_count  / length * 100, 1) if length > 0 else 0,
        'sheet':  round(sheet_count  / length * 100, 1) if length > 0 else 0,
        'turn':   round(turn_count   / length * 100, 1) if length > 0 else 0,
        'coil':   round(coil_count   / length * 100, 1) if length > 0 else 0,
    }

    # Extinction coefficient
    ext_coeff = (
        seq.count('W') * 5500 +
        seq.count('Y') * 1490 +
        seq.count('C') * 125
    )

    return {
        'type': 'PROTEIN',
        'length': length,
        'sequence': seq,
        'molecular_weight': mol_weight,
        'isoelectric_point': pi,
        'hydrophobicity': hydro_score,
        'instability_index': instability_index,
        'is_stable': is_stable,
        'extinction_coefficient': ext_coeff,
        'aa_composition': aa_composition,
        'secondary_structure': secondary_structure,
        'charge_at_ph7': positive_aa - negative_aa,
        'positive_aa': positive_aa,
        'negative_aa': negative_aa,
        'aromatic_aa': seq.count('W') + seq.count('Y') + seq.count('F'),
    }

# ─────────────────────────────────────────────────────────────
# MUTATION DETECTION
# ─────────────────────────────────────────────────────────────
def detect_mutations(seq1, seq2):
    seq1 = seq1.upper().strip()
    seq2 = seq2.upper().strip()

    mutations = []
    max_len = max(len(seq1), len(seq2))
    min_len = min(len(seq1), len(seq2))

    # Substitutions
    for i in range(min_len):
        if seq1[i] != seq2[i]:
            mutations.append({
                'type': 'Substitution',
                'position': i + 1,
                'original': seq1[i],
                'mutated': seq2[i],
                'description': f'Position {i+1}: {seq1[i]} → {seq2[i]}'
            })

    # Insertions/Deletions (length difference)
    if len(seq2) > len(seq1):
        mutations.append({
            'type': 'Insertion',
            'position': min_len + 1,
            'original': '-',
            'mutated': seq2[min_len:],
            'description': f'Insertion at position {min_len+1}: +{seq2[min_len:]}'
        })
    elif len(seq1) > len(seq2):
        mutations.append({
            'type': 'Deletion',
            'position': min_len + 1,
            'original': seq1[min_len:],
            'mutated': '-',
            'description': f'Deletion at position {min_len+1}: -{seq1[min_len:]}'
        })

    similarity = round(
        sum(1 for a, b in zip(seq1, seq2) if a == b) / max_len * 100, 2
    )

    return {
        'sequence1': seq1,
        'sequence2': seq2,
        'mutations': mutations,
        'mutation_count': len(mutations),
        'similarity_percent': similarity,
        'mutation_rate': round(100 - similarity, 2),
        'length1': len(seq1),
        'length2': len(seq2),
    }

# ═══════════════════════════════════════════════════════════════
# API ENDPOINTS
# ═══════════════════════════════════════════════════════════════

@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({'status': 'running', 'message': 'BioSequenceAnalyzer Backend is alive!'})


@app.route('/api/detect', methods=['POST'])
def detect():
    data = request.get_json()
    seq  = data.get('sequence', '').strip()
    if not seq:
        return jsonify({'error': 'No sequence provided'}), 400
    seq_type = detect_sequence_type(seq)
    return jsonify({'type': seq_type, 'length': len(seq)})


@app.route('/api/analyze', methods=['POST'])
def analyze():
    data     = request.get_json()
    sequence = data.get('sequence', '').strip().upper()
    seq_type = data.get('type', '').upper()

    if not sequence:
        return jsonify({'error': 'No sequence provided'}), 400

    # Auto-detect if type not given
    if not seq_type or seq_type == 'AUTO':
        seq_type = detect_sequence_type(sequence)

    if seq_type == 'DNA':
        result = analyze_dna(sequence)
    elif seq_type == 'RNA':
        result = analyze_rna(sequence)
    elif seq_type == 'PROTEIN':
        result = analyze_protein(sequence)
    else:
        return jsonify({'error': f'Unknown sequence type: {seq_type}. Use DNA, RNA, or PROTEIN'}), 400

    return jsonify({'success': True, 'result': result})


@app.route('/api/mutate', methods=['POST'])
def mutate():
    data = request.get_json()
    seq1 = data.get('sequence1', '').strip()
    seq2 = data.get('sequence2', '').strip()

    if not seq1 or not seq2:
        return jsonify({'error': 'Two sequences required for mutation analysis'}), 400

    result = detect_mutations(seq1, seq2)
    return jsonify({'success': True, 'result': result})


@app.route('/api/translate', methods=['POST'])
def translate():
    data = request.get_json()
    seq  = data.get('sequence', '').strip().upper()

    if not seq:
        return jsonify({'error': 'No sequence provided'}), 400

    # Convert DNA to RNA if needed
    if 'T' in seq and 'U' not in seq:
        seq = seq.replace('T', 'U')

    protein, codons = translate_rna(seq)
    return jsonify({
        'success': True,
        'protein': '-'.join(protein),
        'protein_length': len(protein),
        'codons': codons
    })


if __name__ == '__main__':
    print("=" * 55)
    print("  BioSequenceAnalyzer Backend")
    print("  Running at: http://localhost:5001")
    print("  Press Ctrl+C to stop")
    print("=" * 55)
    app.run(debug=True, host='0.0.0.0', port=5001)
