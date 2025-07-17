#!/bin/bash

set -e

clj -M:backend --skolemize $1
