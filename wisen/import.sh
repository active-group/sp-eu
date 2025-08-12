#!/bin/bash

set -e

curl localhost:4321/api/import -X POST -d "$(<$1)"
