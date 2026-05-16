from enum import Enum


class JWKAlg(str, Enum):
    RS256 = "RS256"

    def __str__(self) -> str:
        return str(self.value)
