from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define
from attrs import field as _attrs_field

if TYPE_CHECKING:
    from ..models.health_component import HealthComponent


T = TypeVar("T", bound="HealthReportComponents")


@_attrs_define
class HealthReportComponents:
    """
    Attributes:
        db (HealthComponent):
        redis (HealthComponent):
    """

    db: HealthComponent
    redis: HealthComponent
    additional_properties: dict[str, HealthComponent] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        db = self.db.to_dict()

        redis = self.redis.to_dict()

        field_dict: dict[str, Any] = {}
        for prop_name, prop in self.additional_properties.items():
            field_dict[prop_name] = prop.to_dict()

        field_dict.update(
            {
                "db": db,
                "redis": redis,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.health_component import HealthComponent

        d = dict(src_dict)
        db = HealthComponent.from_dict(d.pop("db"))

        redis = HealthComponent.from_dict(d.pop("redis"))

        health_report_components = cls(
            db=db,
            redis=redis,
        )

        additional_properties = {}
        for prop_name, prop_dict in d.items():
            additional_property = HealthComponent.from_dict(prop_dict)

            additional_properties[prop_name] = additional_property

        health_report_components.additional_properties = additional_properties
        return health_report_components

    @property
    def additional_keys(self) -> list[str]:
        return list(self.additional_properties.keys())

    def __getitem__(self, key: str) -> HealthComponent:
        return self.additional_properties[key]

    def __setitem__(self, key: str, value: HealthComponent) -> None:
        self.additional_properties[key] = value

    def __delitem__(self, key: str) -> None:
        del self.additional_properties[key]

    def __contains__(self, key: str) -> bool:
        return key in self.additional_properties
