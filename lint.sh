#!/bin/bash

echo "🔍 Running ktlint check..."
./gradlew ktlintCheck

echo ""
echo "🔧 Auto-fixing ktlint issues..."
./gradlew ktlintFormat

echo ""
echo "✅ Linting complete!" 
