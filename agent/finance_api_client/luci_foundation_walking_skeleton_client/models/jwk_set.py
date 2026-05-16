from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.jwk import JWK


T = TypeVar("T", bound="JWKSet")


@_attrs_define
class JWKSet:
    """IETF JWK Set (RFC 7517).

    Attributes:
        keys (list[JWK]):
    """

    keys: list[JWK]

    def to_dict(self) -> dict[str, Any]:
        keys = []
        for keys_item_data in self.keys:
            keys_item = keys_item_data.to_dict()
            keys.append(keys_item)

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "keys": keys,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.jwk import JWK

        d = dict(src_dict)
        keys = []
        _keys = d.pop("keys")
        for keys_item_data in _keys:
            keys_item = JWK.from_dict(keys_item_data)

            keys.append(keys_item)

        jwk_set = cls(
            keys=keys,
        )

        return jwk_set
