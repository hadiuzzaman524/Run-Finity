#!/bin/bash
git config core.hooksPath scripts/git-hooks
chmod +x scripts/git-hooks/*
echo "✅ Git hooks configured!"