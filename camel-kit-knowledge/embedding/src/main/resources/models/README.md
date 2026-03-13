# ONNX Model Files

The model files are not stored in git (too large). They are downloaded automatically
by `maven-download-plugin` during the MCP module build, or manually:

```bash
curl -L -o model_quantized.onnx "https://huggingface.co/onnx-community/granite-embedding-small-english-r2-ONNX/resolve/main/onnx/model_quantized.onnx"
curl -L -o model_quantized.onnx_data "https://huggingface.co/onnx-community/granite-embedding-small-english-r2-ONNX/resolve/main/onnx/model_quantized.onnx_data"
```

- **Model:** ibm-granite/granite-embedding-small-english-r2 (ONNX Q8 quantized)
- **Dimensions:** 384
- **Max sequence length:** 8,192 tokens (capped at 512 in OnnxEmbeddingProvider)
- **Size:** ~52 MB (graph: 0.6 MB + weights: 49 MB)
- **Architecture:** ModernBERT (12 layers, 12 heads, 47M params)
