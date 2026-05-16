from enum import Enum


class HealthStatus(str, Enum):
    DOWN = "DOWN"
    UP = "UP"

    def __str__(self) -> str:
        return str(self.value)
