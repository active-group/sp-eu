#!/bin/bash

set -e

for file in "$1"/*.jsonld; do ./import.sh "$file"; done

