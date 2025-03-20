#!/bin/bash

echo "Waiting for Ollama server..."
while ! curl -s "${OLLAMA_HOST:-http://ollama:11434}" > /dev/null; do
    sleep 2
done

# Check if the model exists, pull it if not
if ! curl -s "${OLLAMA_HOST:-http://ollama:11434}/api/tags" | grep -q "$OLLAMA_MODEL"; then
    echo "Pulling Ollama model: $OLLAMA_MODEL"
    curl -X POST "${OLLAMA_HOST:-http://ollama:11434}/api/pull" -d "{\"model\":\"$OLLAMA_MODEL\"}"

    # Wait for the model to be fully pulled
    while ! curl -s "${OLLAMA_HOST:-http://ollama:11434}/api/tags" | grep -q "$OLLAMA_MODEL"; do
        echo "Waiting for model to be ready..."
        sleep 5
    done
fi

echo "Ollama model is ready. Starting wisen app..."
java -jar /app/app.jar
