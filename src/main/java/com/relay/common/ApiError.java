package com.relay.common;

import java.util.List;

public record ApiError(String message, List<String> details) {
}