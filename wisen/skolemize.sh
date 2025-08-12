#!/bin/bash

set -e

curl http://localhost:4321/api/skolemize -X POST -d "$(<$1)"
