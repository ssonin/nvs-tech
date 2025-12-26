from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
  title="Embedding Service",
  description="Generates text embeddings using all-MiniLM-L6-v2",
)

logger.info("Loading sentence-transformer model...")
model = SentenceTransformer("all-MiniLM-L6-v2")
logger.info("Model loaded successfully")

class EmbeddingRequest(BaseModel):
  texts: list[str] = Field(..., min_length=1, max_length=100)

class EmbeddingResponse(BaseModel):
  embeddings: list[list[float]]

@app.get("/health")
def health_check():
  return {"status": "healthy"}

@app.post("/embeddings", response_model=EmbeddingResponse)
def get_embeddings(request: EmbeddingRequest):
  if not request.texts:
    raise HTTPException(status_code=400, detail="texts cannot be empty")

  try:
    embeddings = model.encode(request.texts)
    return EmbeddingResponse(embeddings=embeddings.tolist())
  except Exception as e:
    logger.exception("Failed to generate embeddings")
    raise HTTPException(status_code=500, detail=str(e))
