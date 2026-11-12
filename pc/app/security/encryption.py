"""Security boundary reserved for the transport encryption stage."""


def encrypt(data: bytes, key: bytes) -> bytes:
    raise NotImplementedError("encryption is not part of Stage 1")


def decrypt(data: bytes, key: bytes) -> bytes:
    raise NotImplementedError("encryption is not part of Stage 1")
