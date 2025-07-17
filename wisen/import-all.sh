#!/bin/bash

set -e

for file in ../jsonld/*.jsonld.skolemized; do ./import.sh "$file"; done

