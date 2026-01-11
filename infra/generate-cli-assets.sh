#!/bin/bash

./gradlew :cli:flamingock-cli:assemble
echo "Generated CLI assets in ./cli/flamingock-cli/build/distributions/"
echo "Uploaded CLI assets to the Github release. Don't forget the checksum files!"
