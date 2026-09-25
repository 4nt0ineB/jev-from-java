"""Open-Jev 2B on a rented Modal GPU, behind Jev's wire format, for the snake page.

    modal deploy selfhost/openjev_modal.py     # start.sh does this, then stops the app on exit

open-jev-serve has no authentication and a Modal URL is public, so every route here checks the
Bearer token start.sh generates per run (Modal secret below): a stranger cannot wake the GPU.
Dependency pins are the ones typesafe-sandbox's bench runs Open-Jev with on Modal.
"""

import hmac
import os

import modal

CHECKPOINT = "ZefanCai/Open-Jev-2B"
REVISION = "0c7aa498b1627be8da4acf34c863ff0ee0a92785"
# 24 GB is plenty for 2B in bf16. Open-Jev-9B (revision 47e96688...) wants "A100-80GB".
GPU = "L4"
HF_HOME = "/root/hf"

image = (
    modal.Image.debian_slim(python_version="3.12")
    .apt_install("git")
    .pip_install("torch==2.14.0", "transformers==5.17.0", "peft==0.19.1", "accelerate==1.13.0",
                 "open-jev @ git+https://github.com/Zefan-Cai/Open-Jev@ed45657bf726c3b77408942830e5578f99df904e",
                 "fastapi[standard]==0.141.1")
    # Without it transformers runs Qwen3.5's linear-attention layers in reference PyTorch.
    .pip_install("flash-linear-attention==0.5.2")
    .env({"HF_HOME": HF_HOME})
)
app = modal.App("jev-snake-openjev", image=image)
weights = modal.Volume.from_name("jev-snake-hf", create_if_missing=True)


# One container at most: a burst of requests queues instead of renting more GPUs.
@app.cls(gpu=GPU, volumes={HF_HOME: weights}, secrets=[modal.Secret.from_name("jev-snake-openjev-key")],
         max_containers=1, scaledown_window=300, timeout=600)
class OpenJev:
    @modal.enter()
    def load(self):
        from huggingface_hub import snapshot_download
        from jev.serving import load_predictor

        root = snapshot_download(CHECKPOINT, revision=REVISION, allow_patterns=["package/checkpoint/*"])
        # Prefix caching stays off, as upstream ships it: it broke their probability tolerance.
        self.predictor = load_predictor(checkpoint=f"{root}/package/checkpoint", device="cuda",
                                        batch_size=32, prefix_cache=False)

    @modal.asgi_app()
    def web(self):
        from fastapi import FastAPI, HTTPException, Request
        from fastapi.responses import JSONResponse

        api = FastAPI()
        expected = "Bearer " + os.environ["OPENJEV_API_KEY"]

        @api.middleware("http")
        async def bearer(request: Request, call_next):
            if not hmac.compare_digest(request.headers.get("authorization", ""), expected):
                return JSONResponse({"error": "unauthorized"}, status_code=401)
            return await call_next(request)

        @api.get("/health")
        def health():
            return {"status": "ready", "model": self.predictor.model_name}

        # Plain def: FastAPI runs it off the event loop, and the container takes one input at a time.
        @api.post("/v1/systemone")
        def systemone(body: dict):
            try:
                return self.predictor.predict({"state": body["state"], "questions": body["questions"]})
            except (ValueError, KeyError, TypeError) as error:
                raise HTTPException(status_code=422, detail=str(error))

        return api
