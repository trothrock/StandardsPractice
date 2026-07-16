package com.rothrockware.studyjazzstandards

import com.rothrockware.studyjazzstandards.data.store.BlobStore

/** localStorage-backed store; the key matches the web app so an existing DB carries over. */
expect fun webBlobStore(): BlobStore
