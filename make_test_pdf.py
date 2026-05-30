import sys, zlib

def make_pdf(path, lines):
    # Build content stream
    content = "BT /F1 18 Tf 72 740 Td 22 TL\n"
    for ln in lines:
        # escape parens
        s = ln.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        content += f"({s}) Tj T*\n"
    content += "ET"
    cbytes = content.encode("latin-1")

    objs = []
    objs.append(b"<< /Type /Catalog /Pages 2 0 R >>")
    objs.append(b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
    objs.append(b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                b"/Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>")
    objs.append(b"<< /Length " + str(len(cbytes)).encode() + b" >>\nstream\n" + cbytes + b"\nendstream")
    objs.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")

    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for i, body in enumerate(objs, start=1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n".encode() + body + b"\nendobj\n"

    xref_pos = len(out)
    n = len(objs) + 1
    out += f"xref\n0 {n}\n".encode()
    out += b"0000000000 65535 f \n"
    for off in offsets:
        out += f"{off:010d} 00000 n \n".encode()
    out += (b"trailer\n<< /Size " + str(n).encode() + b" /Root 1 0 R >>\n"
            b"startxref\n" + str(xref_pos).encode() + b"\n%%EOF")

    with open(path, "wb") as f:
        f.write(out)
    print("wrote", path, len(out), "bytes")

make_pdf("test_qpaper_original.pdf", [
    "PSG iTech - TEST QUESTION PAPER",
    "*** THIS IS A TEST UPLOAD - NOT A REAL PAPER ***",
    "",
    "Subject: Test Subject",
    "Version: ORIGINAL (as contributed by student)",
    "",
    "Q1. This is the original uploaded version.",
    "Q2. Awaiting admin review.",
])

make_pdf("test_qpaper_edited.pdf", [
    "PSG iTech - TEST QUESTION PAPER",
    "*** THIS IS A TEST UPLOAD - NOT A REAL PAPER ***",
    "",
    "Subject: Test Subject",
    "Version: EDITED BY ADMIN (replaced before approval)",
    "",
    "Q1. This version was edited and replaced by admin.",
    "Q2. If you see EDITED, the Replace+Approve flow worked.",
])
