#!/bin/bash

set -e

for file in ../jsonld/*.jsonld; do
    skolemized="${file}.skolemized"

    if [[ ! -e "$skolemized" ]]; then
      ./skolemize.sh "$file"
    fi
done

