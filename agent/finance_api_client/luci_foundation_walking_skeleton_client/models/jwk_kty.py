from enum import Enum


class JWKKty(str, Enum):
    RSA = "RSA"

    def __str__(self) -> str:
        return str(self.value)
