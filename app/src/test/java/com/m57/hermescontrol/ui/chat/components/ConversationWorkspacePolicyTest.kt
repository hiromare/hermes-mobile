package com.m57.hermescontrol.ui.chat.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prevents the new context shortcuts from silently abandoning a chat draft. */
class ConversationWorkspacePolicyTest {
    @Test
    fun emptyChat_canSwitch() {
        assertFalse(workspaceHasUnsavedDraft("", 0, isSending = false))
    }

    @Test
    fun textDraft_blocksNavigation() {
        assertTrue(workspaceHasUnsavedDraft("unfinished research question", 0, isSending = false))
    }

    @Test
    fun imageAttachment_blocksNavigationEvenWithoutText() {
        assertTrue(workspaceHasUnsavedDraft("", 1, isSending = false))
    }

    @Test
    fun ongoingSend_blocksNavigation() {
        assertTrue(workspaceHasUnsavedDraft("", 0, isSending = true))
    }

    @Test
    fun whitespaceOnlyDoesNotBlock() {
        assertFalse(workspaceHasUnsavedDraft(" \n  ", 0, isSending = false))
    }
}
