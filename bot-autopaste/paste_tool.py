"""Bot AutoPaste -- baca transaksi dari clipboard, catat otomatis ke Google Sheets.

Alur: buka aplikasi PPOB/admin panel di browser, select & copy detail
transaksi, lalu klik tombol "Baca Clipboard & Catat" di jendela kecil ini.
Tidak perlu isi apa pun manual -- teksnya diparse otomatis lalu dikirim ke
backend NotifLogger (kategori AUTOPASTE) lewat HTTP POST, sama seperti
notifikasi dari aplikasi Android.
"""

import json
import re
import tkinter as tk
from tkinter import font as tkfont
from tkinter import ttk
from urllib.request import Request, urlopen
from urllib.error import URLError

WEB_APP_URL = (
    "https://script.google.com/macros/s/"
    "AKfycbwmaD_puo3nZPzU5sfC84raKvut2bEkJSXviUO-PVyXY4g02fJ-7gBrRy2VFU6MOUN7mA/exec"
)
CATEGORY = "AUTOPASTE"

# Nama tab persis seperti di Google Sheets (case-sensitive), supaya cocok
# dengan getSheetByName di backend. TEST 1/TEST 2/Sheet3 sudah di-rename jadi
# tab per-bank ini (2026-08-09).
SHEET_OPTIONS = ["BCA", "BRI", "MANDIRI", "BNI", "DANA", "OVO", "LINKAJA", "GOPAY"]
DEFAULT_SHEET = "BRI"

# Label channel yang muncul sebagai penanda blok "channel / nama / no HP" di
# clipboard. Nama pengirim yang diambil adalah baris tepat SETELAH channel
# pertama yang ditemukan (blok kedua, kalau ada, diabaikan -- itu biasanya
# nama admin/approver, bukan nama pengirim dana).
CHANNEL_KEYWORDS = {"DANA", "OVO", "GOPAY", "LINKAJA", "BCA", "BRI", "MANDIRI", "BNI"}


class ParseError(Exception):
    pass


def parse_clipboard(text):
    """Ambil (user_id, nama_pengirim, nominal) dari teks clipboard transaksi.

    Aturan (dikonfirmasi dari contoh nyata pengguna):
    - Baris PERTAMA di clipboard adalah identitas/kode akun (mis.
      "AHERIMUSTAFA07", "ARGONTARA") -- beda-beda per transaksi, dicatat apa
      adanya ke kolom User ID.
    - Setelah itu ada beberapa baris "Rp <angka>" berdiri sendiri; yang
      PERTAMA adalah kode/referensi/fee yang diabaikan, yang KEDUA adalah
      nominal transaksi yang sebenarnya.
    - Ada satu atau lebih blok 3 baris berurutan: nama channel (mis. DANA),
      lalu nama pengirim, lalu nomor HP. Blok PERTAMA dipakai (nama
      pengirim), blok berikutnya (biasanya nama admin/approver) diabaikan.
    - Baris "DP Approve : ..." / "DP Reject : ..." dan tanggal/jam diabaikan
      sepenuhnya -- Jam Catat & Jam Kasih diisi otomatis pakai waktu saat
      tombol diklik (transaksi yang di-paste dianggap sudah "DP Approve").
    """
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    if not lines:
        raise ParseError("Clipboard kosong.")

    user_id = lines[0]
    rest = lines[1:]

    rp_lines = [l for l in rest if re.fullmatch(r"Rp\.?\s*[\d.,]+", l, re.IGNORECASE)]
    if len(rp_lines) < 2:
        raise ParseError(
            "Tidak ketemu 2 baris nominal ('Rp ...') di clipboard. "
            "Pastikan sudah copy detail transaksi yang lengkap."
        )
    amount_digits = re.sub(r"[^\d]", "", rp_lines[1])
    if not amount_digits:
        raise ParseError("Baris nominal kedua tidak berisi angka yang valid.")

    name = None
    for i, line in enumerate(rest):
        if line.upper() in CHANNEL_KEYWORDS and i + 1 < len(rest):
            name = rest[i + 1]
            break
    if not name:
        raise ParseError(
            "Tidak ketemu nama pengirim (baris setelah channel seperti DANA/OVO/dst.)."
        )

    return user_id, name, amount_digits


def send_to_sheet(user_id, name, amount, sheet_name):
    payload = json.dumps(
        {
            "action": "log_manual",
            "category": CATEGORY,
            "sheetName": sheet_name,
            "userId": user_id,
            "name": name,
            "amount": amount,
        }
    ).encode("utf-8")
    req = Request(WEB_APP_URL, data=payload, headers={"Content-Type": "application/json"})
    with urlopen(req, timeout=60) as resp:
        body = json.loads(resp.read().decode("utf-8"))
    return body


class App:
    def __init__(self, root):
        self.root = root
        root.title("Bot AutoPaste")
        root.attributes("-topmost", True)
        root.resizable(False, False)
        root.configure(bg="#1b1b1b")

        title_font = tkfont.Font(family="Segoe UI", size=12, weight="bold")
        status_font = tkfont.Font(family="Segoe UI", size=10)

        tk.Label(
            root, text="Bot AutoPaste",
            font=title_font, bg="#1b1b1b", fg="#f2c14e", pady=10,
        ).pack(fill="x", padx=16)

        self.btn = tk.Button(
            root, text="\U0001F4CB  Baca Clipboard & Catat", font=title_font,
            bg="#f2c14e", fg="#1b1b1b", activebackground="#d9a93e",
            relief="flat", padx=16, pady=10, command=self.on_click,
        )
        self.btn.pack(fill="x", padx=16, pady=(0, 10))

        sheet_row = tk.Frame(root, bg="#1b1b1b")
        sheet_row.pack(fill="x", padx=16, pady=(0, 10))

        tk.Label(
            sheet_row, text="Sheet tujuan:", font=status_font,
            bg="#1b1b1b", fg="#cccccc",
        ).pack(side="left")

        self.sheet_var = tk.StringVar(value=DEFAULT_SHEET)
        self.sheet_combo = ttk.Combobox(
            sheet_row, textvariable=self.sheet_var, values=SHEET_OPTIONS,
            state="readonly", width=14, font=status_font,
        )
        self.sheet_combo.pack(side="left", padx=(8, 0))

        self.status = tk.Label(
            root, text="Siap. Copy transaksi lalu klik tombol di atas.",
            font=status_font, bg="#1b1b1b", fg="#cccccc",
            wraplength=320, justify="left", pady=8,
        )
        self.status.pack(fill="x", padx=16, pady=(0, 14))

    def set_status(self, text, color="#cccccc"):
        self.status.configure(text=text, fg=color)
        self.root.update_idletasks()

    def on_click(self):
        self.btn.configure(state="disabled")
        try:
            try:
                clip = self.root.clipboard_get()
            except tk.TclError:
                self.set_status("Clipboard kosong / bukan teks.", "#e05d5d")
                return

            try:
                user_id, name, amount = parse_clipboard(clip)
            except ParseError as e:
                self.set_status(f"Gagal baca: {e}", "#e05d5d")
                return

            sheet_name = self.sheet_var.get()
            formatted = f"Rp{int(amount):,}".replace(",", ".")
            self.set_status(
                f"Mengirim ke {sheet_name}: {user_id} / {name} - {formatted} ...", "#f2c14e"
            )

            try:
                result = send_to_sheet(user_id, name, amount, sheet_name)
            except URLError as e:
                self.set_status(f"Gagal kirim (jaringan): {e}", "#e05d5d")
                return

            if result.get("status") == "ok":
                self.set_status(
                    f"✓ Tercatat ke {sheet_name}: {user_id} / {name} - {formatted}", "#7dd87d"
                )
            else:
                self.set_status(f"✗ Ditolak server: {result.get('message')}", "#e05d5d")
        finally:
            self.btn.configure(state="normal")


if __name__ == "__main__":
    root = tk.Tk()
    App(root)
    root.mainloop()
