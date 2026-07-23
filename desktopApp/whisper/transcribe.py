#!/usr/bin/env python3
import sys
import argparse
import json
from faster_whisper import WhisperModel

def transcribe_audio(audio_path, lang="es", model_size="small", compute_type="int8"):
    try:
        model = WhisperModel(model_size, device="cpu", compute_type=compute_type)
        segments, info = model.transcribe(audio_path, language=lang, beam_size=5)
        text = " ".join([segment.text.strip() for segment in segments]).strip()
        print(text)
    except Exception as e:
        sys.stderr.write(f"Error transcribing audio: {e}\n")
        sys.exit(1)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Faster Whisper Transcriber")
    parser.add_argument("--audio", required=True, help="Path to audio file (WAV)")
    parser.add_argument("--lang", default="es", help="Language code (e.g. es, en)")
    parser.add_argument("--model", default="small", help="Whisper model size")
    parser.add_argument("--compute_type", default="int8", help="Compute type (e.g. int8)")
    
    args = parser.parse_args()
    transcribe_audio(args.audio, lang=args.lang, model_size=args.model, compute_type=args.compute_type)
