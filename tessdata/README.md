# Tesseract 语言包（本地图片 OCR）

本目录用于 **Tess4J** 本地识图，不提交到 Git（见根目录 `.gitignore`）。

## 一键下载

在仓库根目录执行：

```powershell
.\scripts\setup-tessdata.ps1
```

将下载 `eng.traineddata`、`chi_sim.traineddata`，并设置当前终端的 `TESSDATA_PREFIX`。

## 其它方式

- 安装 [Tesseract](https://github.com/tesseract-ocr/tesseract) 后使用自带 `tessdata`（如 `C:\Program Files\Tesseract-OCR\tessdata`）
- 或设置环境变量 **`TESSDATA_PREFIX`** 指向含 `eng.traineddata` 的目录

重启后端后，日志出现 `[本地图片OCR] 已启用` 即表示本机 OCR 就绪。
