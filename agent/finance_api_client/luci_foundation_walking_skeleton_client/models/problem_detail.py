from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

from ..types import UNSET, Unset

T = TypeVar("T", bound="ProblemDetail")


@_attrs_define
class ProblemDetail:
    """Minimal RFC 7807-style error body. The skeleton uses it only for
    `401`; downstream features may extend with `type`/`detail`. No
    stack traces (FR-018, constitution: no raw stack-trace returns).

        Attributes:
            status (int):
            title (str | Unset):
    """

    status: int
    title: str | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        status = self.status

        title = self.title

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "status": status,
            }
        )
        if title is not UNSET:
            field_dict["title"] = title

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        status = d.pop("status")

        title = d.pop("title", UNSET)

        problem_detail = cls(
            status=status,
            title=title,
        )

        return problem_detail
