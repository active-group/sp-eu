#!/bin/bash

set -e

for file in "$1"/*.jsonld; do
    skolemized="${file}.skolemized"

    if [[ ! -e "$skolemized" ]]; then
      ./skolemize.sh "$file" > "$skolemized"
    fi
done

