from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar
from uuid import UUID

from attrs import define as _attrs_define

T = TypeVar("T", bound="WhoamiResponse")


@_attrs_define
class WhoamiResponse:
    """Echo of the verified claims. Read-only.

    Attributes:
        subject (str): The `sub` claim from the verified JWT.
        user_id (UUID): The `user_id` claim (UUID) from the verified JWT.
        intent (str): The `intent` claim from the verified JWT.
        trace_id (str): The `trace_id` claim from the verified JWT.
    """

    subject: str
    user_id: UUID
    intent: str
    trace_id: str

    def to_dict(self) -> dict[str, Any]:
        subject = self.subject

        user_id = str(self.user_id)

        intent = self.intent

        trace_id = self.trace_id

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "subject": subject,
                "user_id": user_id,
                "intent": intent,
                "trace_id": trace_id,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        subject = d.pop("subject")

        user_id = UUID(d.pop("user_id"))

        intent = d.pop("intent")

        trace_id = d.pop("trace_id")

        whoami_response = cls(
            subject=subject,
            user_id=user_id,
            intent=intent,
            trace_id=trace_id,
        )

        return whoami_response
