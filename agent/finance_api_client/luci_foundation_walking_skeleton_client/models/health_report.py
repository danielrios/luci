from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define
from attrs import field as _attrs_field

from ..models.health_status import HealthStatus

if TYPE_CHECKING:
    from ..models.health_report_components import HealthReportComponents


T = TypeVar("T", bound="HealthReport")


@_attrs_define
class HealthReport:
    """Spring Boot Actuator `Health` shape. Mirrored byte-for-byte at the
    `status` + `components.db.status` + `components.redis.status`
    level by the Python agent's hand-written endpoint.

        Attributes:
            status (HealthStatus): Per-component or aggregate status. The top-level `status` is
                `DOWN` whenever any required component is `DOWN`.
            components (HealthReportComponents):
    """

    status: HealthStatus
    components: HealthReportComponents
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        status = self.status.value

        components = self.components.to_dict()

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "status": status,
                "components": components,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.health_report_components import HealthReportComponents

        d = dict(src_dict)
        status = HealthStatus(d.pop("status"))

        components = HealthReportComponents.from_dict(d.pop("components"))

        health_report = cls(
            status=status,
            components=components,
        )

        health_report.additional_properties = d
        return health_report

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
