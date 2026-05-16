from http import HTTPStatus
from typing import Any

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.problem_detail import ProblemDetail
from ...models.whoami_body import WhoamiBody
from ...models.whoami_response import WhoamiResponse
from ...types import UNSET, Response, Unset


def _get_kwargs(
    *,
    body: WhoamiBody | Unset = UNSET,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}

    _kwargs: dict[str, Any] = {
        "method": "post",
        "url": "/v1/internal/whoami",
    }

    if not isinstance(body, Unset):
        _kwargs["json"] = body.to_dict()

    headers["Content-Type"] = "application/json"

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ProblemDetail | WhoamiResponse | None:
    if response.status_code == 200:
        response_200 = WhoamiResponse.from_dict(response.json())

        return response_200

    if response.status_code == 401:
        response_401 = ProblemDetail.from_dict(response.json())

        return response_401

    if client.raise_on_unexpected_status:
        raise errors.UnexpectedStatus(response.status_code, response.content)
    else:
        return None


def _build_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> Response[ProblemDetail | WhoamiResponse]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient,
    body: WhoamiBody | Unset = UNSET,
) -> Response[ProblemDetail | WhoamiResponse]:
    """Echo verified M2M JWT claims back to the caller.

     This endpoint exists solely to validate the M2M auth seam (FR-020).
    It performs no business logic. It MUST be reachable in local; in
    prod it is gated to the platform-internal network or behind the
    same allow-list as the rest of `/v1/internal/*` (FR-020, A-7).

    Args:
        body (WhoamiBody | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ProblemDetail | WhoamiResponse]
    """

    kwargs = _get_kwargs(
        body=body,
    )

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    *,
    client: AuthenticatedClient,
    body: WhoamiBody | Unset = UNSET,
) -> ProblemDetail | WhoamiResponse | None:
    """Echo verified M2M JWT claims back to the caller.

     This endpoint exists solely to validate the M2M auth seam (FR-020).
    It performs no business logic. It MUST be reachable in local; in
    prod it is gated to the platform-internal network or behind the
    same allow-list as the rest of `/v1/internal/*` (FR-020, A-7).

    Args:
        body (WhoamiBody | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ProblemDetail | WhoamiResponse
    """

    return sync_detailed(
        client=client,
        body=body,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient,
    body: WhoamiBody | Unset = UNSET,
) -> Response[ProblemDetail | WhoamiResponse]:
    """Echo verified M2M JWT claims back to the caller.

     This endpoint exists solely to validate the M2M auth seam (FR-020).
    It performs no business logic. It MUST be reachable in local; in
    prod it is gated to the platform-internal network or behind the
    same allow-list as the rest of `/v1/internal/*` (FR-020, A-7).

    Args:
        body (WhoamiBody | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ProblemDetail | WhoamiResponse]
    """

    kwargs = _get_kwargs(
        body=body,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient,
    body: WhoamiBody | Unset = UNSET,
) -> ProblemDetail | WhoamiResponse | None:
    """Echo verified M2M JWT claims back to the caller.

     This endpoint exists solely to validate the M2M auth seam (FR-020).
    It performs no business logic. It MUST be reachable in local; in
    prod it is gated to the platform-internal network or behind the
    same allow-list as the rest of `/v1/internal/*` (FR-020, A-7).

    Args:
        body (WhoamiBody | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ProblemDetail | WhoamiResponse
    """

    return (
        await asyncio_detailed(
            client=client,
            body=body,
        )
    ).parsed
