package it.federicorapetti.recalls.data

/** GLOSSARY: History window; recalls older than this (relative to now) are never stored or indexed. */
const val HISTORY_WINDOW_MS = 90L * 24 * 60 * 60 * 1000
