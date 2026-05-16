from http import HTTPStatus
from typing import Any

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.jwk_set import JWKSet
from ...types import Response


def _get_kwargs() -> dict[str, Any]:

    _kwargs: dict[str, Any] = {
        "method": "get",
        "url": "/.well-known/jwks.json",
    }

    return _kwargs


def _parse_response(*, client: AuthenticatedClient | Client, response: httpx.Response) -> JWKSet | None:
    if response.status_code == 200:
        response_200 = JWKSet.from_dict(response.json())

        return response_200

    if client.raise_on_unexpected_status:
        raise errors.UnexpectedStatus(response.status_code, response.content)
    else:
        return None


def _build_response(*, client: AuthenticatedClient | Client, response: httpx.Response) -> Response[JWKSet]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient | Client,
) -> Response[JWKSet]:
    """Publish the public key set Spring uses to verify M2M JWTs.

     Standard JWK Set (RFC 7517). Unauthenticated. Reachable on
    `localhost` in the local profile; in prod, restricted to the
    platform-internal network or the same allow-list as
    `/v1/internal/*` (FR-019). Spring's own `NimbusJwtDecoder` fetches
    this URL via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
    (R-3).

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[JWKSet]
    """

    kwargs = _get_kwargs()

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    *,
    client: AuthenticatedClient | Client,
) -> JWKSet | None:
    """Publish the public key set Spring uses to verify M2M JWTs.

     Standard JWK Set (RFC 7517). Unauthenticated. Reachable on
    `localhost` in the local profile; in prod, restricted to the
    platform-internal network or the same allow-list as
    `/v1/internal/*` (FR-019). Spring's own `NimbusJwtDecoder` fetches
    this URL via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
    (R-3).

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        JWKSet
    """

    return sync_detailed(
        client=client,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient | Client,
) -> Response[JWKSet]:
    """Publish the public key set Spring uses to verify M2M JWTs.

     Standard JWK Set (RFC 7517). Unauthenticated. Reachable on
    `localhost` in the local profile; in prod, restricted to the
    platform-internal network or the same allow-list as
    `/v1/internal/*` (FR-019). Spring's own `NimbusJwtDecoder` fetches
    this URL via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
    (R-3).

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[JWKSet]
    """

    kwargs = _get_kwargs()

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient | Client,
) -> JWKSet | None:
    """Publish the public key set Spring uses to verify M2M JWTs.

     Standard JWK Set (RFC 7517). Unauthenticated. Reachable on
    `localhost` in the local profile; in prod, restricted to the
    platform-internal network or the same allow-list as
    `/v1/internal/*` (FR-019). Spring's own `NimbusJwtDecoder` fetches
    this URL via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
    (R-3).

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        JWKSet
    """

    return (
        await asyncio_detailed(
            client=client,
        )
    ).parsed
