# Converting documents to markdown

The research docs live in `docs/research/`. When a PDF or other document needs to become a reusable
markdown file, use the project venv:

```bash
.venv/bin/markitdown path/to/file.pdf -o docs/research/file.md
```

`.venv` is gitignored. `markitdown[pdf]` is installed; if a PDF needs layout preservation rather than
plain text, also try `pdftotext` (poppler-utils) or `pymupdf4llm`.

Sources already captured (2026-11-02):

| File | What it is |
|---|---|
| `whoop-api.md` | WHOOP REST API: auth, scopes, endpoints, pagination, what is missing (no intraday HR), rate limits, webhooks, BLE broadcast |
| `visible-scoring.md` | Visible's published scoring description + the formulas we implement |
| `visible-app-behavior.md` | Screen-by-screen behaviour spec derived from the public product |
| `../../Visible vs. WHOOP Wearable Comparison.md` | The original Gemini comparison and algorithm sketch |

Still external (fetch on demand, do not commit unless needed):

- WHOOP API reference: https://developer.whoop.com/api/
- WHOOP OAuth: https://developer.whoop.com/docs/developing/oauth/
- WHOOP webhooks: https://developer.whoop.com/docs/developing/webhooks
- WHOOP rate limits: https://developer.whoop.com/docs/developing/rate-limiting
- Visible blog: https://www.makevisible.com/blog
- Sawyer et al. 2024 (Visible survey): https://doi.org/10.21203/rs.3.rs-5389182/v1
- Schyvens et al. 2024 (WHOOP vs PSG): https://doi.org/10.21206/52192
