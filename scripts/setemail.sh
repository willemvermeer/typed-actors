#!/bin/bash

curl -X POST "http://localhost:8080/session/$1?email=$2"
