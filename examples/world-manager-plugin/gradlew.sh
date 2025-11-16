#!/bin/bash

# Gradle wrapper
if [ ! -f gradlew ]; then
    gradle wrapper --gradle-version 8.3
fi