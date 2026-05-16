from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define
from attrs import field as _attrs_field

from ..models.health_status import HealthStatus
from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.health_component_details import HealthComponentDetails


T = TypeVar("T", bound="HealthComponent")


@_attrs_define
class HealthComponent:
    """
    Attributes:
        status (HealthStatus): Per-component or aggregate status. The top-level `status` is
            `DOWN` whenever any required component is `DOWN`.
        details (HealthComponentDetails | Unset): Implementation-defined per indicator. Not required.
    """

    status: HealthStatus
    details: HealthComponentDetails | Unset = UNSET
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        status = self.status.value

        details: dict[str, Any] | Unset = UNSET
        if not isinstance(self.details, Unset):
            details = self.details.to_dict()

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "status": status,
            }
        )
        if details is not UNSET:
            field_dict["details"] = details

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.health_component_details import HealthComponentDetails

        d = dict(src_dict)
        status = HealthStatus(d.pop("status"))

        _details = d.pop("details", UNSET)
        details: HealthComponentDetails | Unset
        if isinstance(_details, Unset):
            details = UNSET
        else:
            details = HealthComponentDetails.from_dict(_details)

        health_component = cls(
            status=status,
            details=details,
        )

        health_component.additional_properties = d
        return health_component

    @property
    def additional_keys(self) -> list[str]:
        return list(self.additional_properties.keys())

    def __getitem__(self, key: str) -> Any:
        return self.additional_properties[key]

    def __setitem__(self, key: str, value: Any) -> None:
        self.additional_properties[key] = value

    def __delitem__(self, key: str) -> None:
        del self.additional_properties[key]

    def __contains__(self, key: str) -> bool:
        return key in self.additional_properties
