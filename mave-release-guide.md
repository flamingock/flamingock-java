

# Generate key pair

1. Generate key pair with `gpg --full-generate-key`
2. List keys to obtain id `gpg --list-secret-keys --keyid-format LONG`
3. Export secret key to a file `gpg --export-secret-keys --armor <your-key-id> > private-key.asc`
4. Export public key to a file `gpg --armor --export <your-key-id> > public-key.asc`
5. Send key to a key store `gpg --send-keys --keyserver hkp://keyserver.ubuntu.com <your-key-id>`
