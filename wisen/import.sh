#!/bin/bash

set -e

clj -M:backend --import $1
