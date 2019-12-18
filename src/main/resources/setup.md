~~~~ Create GCP project
~~~~ Enable Cloud build
~~~~ Create trigger
~~~~ Connect github project
~~~~ Create firebase project
~~~~ Create firestore DB, use production mode. Choose location carefully.
~~~~ Download firebase config file config file for your project. https://firebase.google.com/docs/admin/setup
~~~~ Create KMS keyring and key
~~~~ Encrypt secrets.conf.
    gcloud kms encrypt \
      --location global \
      --keyring {PROJECT_ID}-keyring \
      --key {PROJECT_ID}-key \
      --plaintext-file secrets.conf \
      --ciphertext-file {PROJECT_ID}.secrets.enc

~~~~ Encrypt surfsup.json
    gcloud kms encrypt \
      --location global \
      --keyring {PROJECT_ID}-keyring \
      --key {PROJECT_ID}-key \
      --plaintext-file surfsup.json \
      --ciphertext-file {PROJECT_ID}.json.enc
