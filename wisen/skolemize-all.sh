#!/bin/bash

set -e

for file in ../jsonld/*.jsonld; do ./skolemize.sh "$file"; done

