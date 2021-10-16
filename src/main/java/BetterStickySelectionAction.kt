import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.util.Key

class BetterStickySelectionAction : EditorAction(Handler()) {

    internal class Handler : EditorActionHandler() {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
            if (!ourActionsRegistered) {
                val actionManager = EditorActionManager.getInstance()
                mapOf(
                    IdeActions.ACTION_EDITOR_COPY to ::CopyHandler,
                    IdeActions.ACTION_EDITOR_ESCAPE to ::EscapeHandler,
                    IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT to ::LeftOrUpHandler,
                    IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT to ::RightOrDownHandler,
                    IdeActions.ACTION_EDITOR_MOVE_CARET_UP to ::LeftOrUpHandler,
                    IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN to ::RightOrDownHandler
                ).forEach { (name, constructor) -> actionManager.setActionHandler(name, constructor(actionManager.getActionHandler(name))) }

                ourActionsRegistered = true
            }
            if (editor.getUserData(HANDLER_REGISTERED_KEY) == null) {
                editor.caretModel.addCaretListener(MyCaretListener())
                editor.selectionModel.addSelectionListener(MySelectionListener())
                editor.document.addDocumentListener(MyDocumentListener(editor))
                editor.putUserData(HANDLER_REGISTERED_KEY, true)
            }

            toggleStickySelection(editor)
        }

        private class MyDocumentListener(private val editor: Editor) : DocumentListener {
            override fun beforeDocumentChange(event: DocumentEvent) = disable(editor)
        }

        private class MyCaretListener : CaretListener {
            override fun caretPositionChanged(e: CaretEvent) {
                val caret = e.caret ?: return
                if (isDisabled(caret.editor)) return
                val start = getStartPosition(caret) ?: return
                caret.setSelection(start, caret.offset)
            }

            override fun caretAdded(e: CaretEvent) {
                val caret = e.caret ?: return
                disable(caret.editor)
            }
        }

        private class MySelectionListener : SelectionListener {
            override fun selectionChanged(e: SelectionEvent) {
                if (isDisabled(e.editor)) return
                val isRemoved = e.newRange.length == 0
                val caret = e.editor.caretModel.currentCaret
                val startPos = getStartPosition(caret) ?: return
                val isInitialPosition = caret.offset == startPos

                if (isRemoved && !isInitialPosition) caret.setSelection(startPos, caret.offset)
            }
        }

        abstract class UpDownLeftRightHandlerBase(myOriginalHandler: EditorActionHandler) : HandlerBase(myOriginalHandler) {
            public override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
                if (isEnabled(editor)) {
                    // Carets behave like below.
                    // - When caret moves left with selection, caret moves to selection start.
                    // - When caret moves right with selection, caret moves to selection end.
                    // - When caret moves up with selection, caret moves to selection start and then up one line since 212.3116.29.
                    // - When caret moves down with selection, caret moves to selection end and then down one line since 212.3116.29.
                    // These behavior conflicts with this plugin feature. So I remove selection here and make selection in `MySelectionListener`
                    // And, disable before removing and enable after removing because the removing kicks `MySelectionListener`.
                    // See
                    // - https://github.com/JetBrains/intellij-community/blob/d5ce8e7e507c01ef32a3a1f85485a212333586fe/platform/platform-impl/src/com/intellij/openapi/editor/actions/MoveCaretLeftOrRightHandler.java
                    // - https://github.com/JetBrains/intellij-community/blob/d5ce8e7e507c01ef32a3a1f85485a212333586fe/platform/platform-impl/src/com/intellij/openapi/editor/actions/MoveCaretUpOrDownHandler.java
                    disable(editor)
                    editor.caretModel.runForEachCaret { c ->
                        // If remove selection in the case caret doesn't move,
                        // the selection will have gone because `MySelectionListener` isn't called.
                        if (willCaretMove(c, editor)) {
                            return@runForEachCaret
                        }
                        c.removeSelection()
                    }
                    enable(editor)
                }
                myOriginalHandler.execute(editor, caret, dataContext)
            }
            abstract fun willCaretMove(caret: Caret, editor: Editor): Boolean
        }

        class RightOrDownHandler(myOriginalHandler: EditorActionHandler) : UpDownLeftRightHandlerBase(myOriginalHandler) {
            override fun willCaretMove(caret: Caret, editor: Editor): Boolean = caret.offset == editor.document.textLength
        }

        class LeftOrUpHandler(myOriginalHandler: EditorActionHandler) : UpDownLeftRightHandlerBase(myOriginalHandler) {
            override fun willCaretMove(caret: Caret, editor: Editor): Boolean = caret.offset == 0
        }

        class EscapeHandler(myOriginalHandler: EditorActionHandler) : HandlerBase(myOriginalHandler) {
            public override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
                if (isEnabled(editor)) disableAndRemoveSelection(editor)
                else myOriginalHandler.execute(editor, caret, dataContext)
            }
        }

        class CopyHandler(myOriginalHandler: EditorActionHandler) : HandlerBase(myOriginalHandler) {
            public override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
                myOriginalHandler.execute(editor, caret, dataContext)
                if (isEnabled(editor)) disableAndRemoveSelection(editor)
            }
        }

        abstract class HandlerBase(protected val myOriginalHandler: EditorActionHandler) : EditorActionHandler() {
            override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
                    isEnabled(editor) || myOriginalHandler.isEnabled(editor, caret, dataContext)
        }

        companion object {
            private val STICKY_SELECTION_START_KEY = Key.create<Int>("StickySelectionHandler.STICKY_SELECTION_START_KEY")
            private val STICKY_SELECTION_ACTIVE_KEY = Key.create<Boolean>("StickySelectionHandler.STICKY_SELECTION_ACTIVE_KEY")
            private val HANDLER_REGISTERED_KEY = Key.create<Boolean>("StickySelectionHandler.HANDLER_REGISTERED_KEY")
            private var ourActionsRegistered = false

            private fun toggleStickySelection(editor: Editor) =
                    if (isDisabled(editor)) {
                        enable(editor)
                        editor.caretModel.runForEachCaret { putStartPosition(it) }
                    } else {
                        disable(editor)
                        editor.caretModel.runForEachCaret { it.removeSelection() }
                    }

            private fun disableAndRemoveSelection(editor: Editor) {
                disable(editor)
                editor.caretModel.runForEachCaret { it.removeSelection() }
            }

            private fun disable(editor: Editor) = editor.putUserData(STICKY_SELECTION_ACTIVE_KEY, false)
            private fun enable(editor: Editor) = editor.putUserData(STICKY_SELECTION_ACTIVE_KEY, true)
            private fun isEnabled(editor: Editor) = editor.getUserData(STICKY_SELECTION_ACTIVE_KEY) == true
            private fun isDisabled(editor: Editor) = !isEnabled(editor)

            private fun getStartPosition(caret: Caret) = caret.getUserData(STICKY_SELECTION_START_KEY)
            private fun putStartPosition(caret: Caret) = caret.putUserData(STICKY_SELECTION_START_KEY, caret.offset)
        }
    }
}
