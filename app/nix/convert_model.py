import torch
from transformers import AutoModel, AutoTokenizer
import argparse
import os
import sys
import json


def convert_model(output_dir=None, model_name=None, model_revision=None):

    if model_name is None:
        model_name = "BAAI/bge-m3"

    safe_model_name = model_name.replace("/", "-")

    print(f"Loading model {model_name}" +
          (f" at revision {model_revision}" if model_revision else ""))

    tokenizer = AutoTokenizer.from_pretrained(model_name, revision=model_revision)
    model = AutoModel.from_pretrained(
        model_name,
        revision=model_revision,
        torchscript=True,
        return_dict=True
    )
    model.eval()

    if output_dir is None:
        output_dir = os.getcwd()

    os.makedirs(output_dir, exist_ok=True)
    os.makedirs(os.path.join(output_dir, "models"), exist_ok=True)

    print("Creating dummy input for tracing...")
    inputs = tokenizer("This is a test sentence", return_tensors="pt", padding=True, truncation=True)
    input_ids = inputs["input_ids"]
    attention_mask = inputs["attention_mask"]

    print("Tracing model...")
    traced = torch.jit.trace_module(
        model,
        {"forward": (input_ids, attention_mask)},
        check_trace=False
    )

    model_path = os.path.join(output_dir, "models", f"{safe_model_name}-traced.pt")
    traced.save(model_path)
    print(f"Saved traced model to {model_path}")

    tokenizer_path = os.path.join(output_dir, "models", "tokenizer")
    tokenizer.save_pretrained(tokenizer_path)
    print(f"Saved tokenizer to {tokenizer_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Convert Hugging Face model to TorchScript")
    parser.add_argument("--output-dir", type=str, help="Output directory for the converted model")
    parser.add_argument("--model-name", type=str, help="Hugging Face model name")
    parser.add_argument("--model-revision", type=str, help="Specific model revision/commit to use")
    args = parser.parse_args()

    # If running in Nix build environment, use $out if available
    output_dir = args.output_dir
    if output_dir is None and "out" in os.environ:
        output_dir = os.environ["out"]

    torch.use_deterministic_algorithms(True)
    torch.manual_seed(0)

    convert_model(output_dir, args.model_name, args.model_revision)
