README — JavaKartRacers report files
====================================

This folder contains the project report and helpers for the JavaKartRacers project.

Files:
- JavaKartRacers_Report.md — the detailed Markdown report suitable for conversion to Word.
- JavaKartRacers_Report.docx — (not created automatically) convert with Pandoc or open the Markdown in Word.
- create_schema.sql — SQL script to create the minimal DB schema used by the server.

Convert Markdown to Word (recommended: Pandoc)
----------------------------------------------
1. Install Pandoc: https://pandoc.org/installing.html
2. From this folder, run:

```cmd
pandoc JavaKartRacers_Report.md -o JavaKartRacers_Report.docx
```

Or open `JavaKartRacers_Report.md` in Microsoft Word (modern versions) and "Save As" → Word Document (.docx).

Include screenshots
-------------------
Place screenshots in `docs/screenshots/` and reference them in the Markdown before converting. Example:

![Lobby screenshot](screenshots/lobby.png)

Suggested screenshots filenames:
- screenshots/menu.png
- screenshots/lobby.png
- screenshots/race_nitro_before.png
- screenshots/race_nitro_after.png
- screenshots/gameover.png
- screenshots/loading.png

Sample logs
-----------
You can paste relevant parts of the server log into the report under the "Sample logs" section. See `JavaKartRacers_Report.md` for examples.

If you want, I can also generate the .docx for you now (requires pandoc in the environment). If you want a fully styled Word document with screenshots inserted and sample log sections filled, provide the images and I will embed them and create the .docx.
