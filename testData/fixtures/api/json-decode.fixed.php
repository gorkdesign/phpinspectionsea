<?php

function cases_holder($encoded) {
    return [
        json_decode($encoded, true),
        json_decode($encoded, true),
        json_decode($encoded, false),
    ];
}