A metrics report captures a request flow between institutions.

> [!NOTE]
> **Security note**
>
> The exchange follows the governance security rules.

## Flow

Consider the following steps:

- Institution A calls Institution B to fetch the account list.
- Institution B processes the request and reports the interaction.

The figure below summarizes the interaction.

![Report flow](/images/flow/sample-report-flow.jpg)

1. Institution A sends `/accounts/v1/accounts` with header `x-fapi-interaction-id`.
2. Institution B responds and both sides report the event.

```
The header `x-fapi-interaction-id` must be echoed by the server response.
```

| Field           | Description                                     |
| --------------- | ----------------------------------------------- |
| `httpMethod`    | HTTP method used by the request.                |
| `correlationId` | Optional identifier used to correlate the call. |
