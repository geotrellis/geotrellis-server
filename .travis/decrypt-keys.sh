#!/bin/sh

openssl aes-256-cbc \
  -K $encrypted_004be7242a73_key \
  -iv $encrypted_004be7242a73_iv \
  -in travis-deploy-key.enc \
  -out travis-deploy-key \
  -d;

chmod 600 travis-deploy-key;

cp travis-deploy-key ~/.ssh/id_rsa;
