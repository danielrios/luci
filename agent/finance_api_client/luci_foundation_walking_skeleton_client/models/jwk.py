from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

from ..models.jwk_alg import JWKAlg
from ..models.jwk_kty import JWKKty
from ..models.jwk_use import JWKUse

T = TypeVar("T", bound="JWK")


@_attrs_define
class JWK:
    """Single RSA public key in JWK form.

    Attributes:
        kty (JWKKty):
        use (JWKUse):
        alg (JWKAlg):
        kid (str): Stable key identifier, format `luci-m2m-<unix-epoch>`.
        n (str): Base64url-encoded RSA modulus.
        e (str): Base64url-encoded RSA public exponent.
    """

    kty: JWKKty
    use: JWKUse
    alg: JWKAlg
    kid: str
    n: str
    e: str

    def to_dict(self) -> dict[str, Any]:
        kty = self.kty.value

        use = self.use.value

        alg = self.alg.value

        kid = self.kid

        n = self.n

        e = self.e

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "kty": kty,
                "use": use,
                "alg": alg,
                "kid": kid,
                "n": n,
                "e": e,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        kty = JWKKty(d.pop("kty"))

        use = JWKUse(d.pop("use"))

        alg = JWKAlg(d.pop("alg"))

        kid = d.pop("kid")

        n = d.pop("n")

        e = d.pop("e")

        jwk = cls(
            kty=kty,
            use=use,
            alg=alg,
            kid=kid,
            n=n,
            e=e,
        )

        return jwk
