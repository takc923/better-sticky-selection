import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.util.Key

class StickySelectionAction : EditorAction(Handler()) {

    internal class Handler : EditorActionHandler() {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
            if (!ourActionsRegistered) {
                val actionManager = EditorActionManager.getInstance()
                actionManager.setActionHandler(IdeActions.ACTION_EDITOR_COPY, HandlerToDisable(actionManager.getActionHandler(IdeActions.ACTION_EDITOR_COPY)))
                actionManager.setActionHandler(IdeActions.ACTION_EDITOR_ESCAPE, HandlerToDisable(actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ESCAPE)))
                actionManager.setActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT, LeftOrRightHandler(actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT)))
                actionManager.setActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT, LeftOrRightHandler(actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT)))

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
            override fun documentChanged(e: DocumentEvent?) {
                editor.caretModel.currentCaret.putUserData(STICKY_SELECTION_START_KEY, null)
            }
        }

        private class MyCaretListener : CaretListener {
            override fun caretPositionChanged(e: CaretEvent?) {
                val caret = e?.caret ?: return
                val start = caret.getUserData(STICKY_SELECTION_START_KEY) ?: return
                caret.setSelection(start, caret.offset)
            }
        }

        private class MySelectionListener : SelectionListener {
            override fun selectionChanged(e: SelectionEvent?) {
                e ?: return
                val isRemoved = e.newRange.length == 0
                val caret = e.editor.caretModel.currentCaret
                val startPos = caret.getUserData(STICKY_SELECTION_START_KEY) ?: return
                val isInitialPosition = caret.offset == startPos

                if (isRemoved && !isInitialPosition) {
                    caret.setSelection(startPos, caret.offset)
                }
            }
        }

        class LeftOrRightHandler(private val myOriginalHandler: EditorActionHandler) : EditorActionHandler() {
            public override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
                editor.caretModel.runForEachCaret { c ->
                    val startPos = c.getUserData(STICKY_SELECTION_START_KEY)
                    c.putUserData(TMP_STICKY_SELECTION_START_KEY, startPos)
                    c.putUserData(STICKY_SELECTION_START_KEY, null)
                    if (startPos != null) c.removeSelection()
                }
                myOriginalHandler.execute(editor, caret, dataContext)
                editor.caretModel.runForEachCaret { c ->
                    val startPos = c.getUserData(TMP_STICKY_SELECTION_START_KEY)
                    c.putUserData(STICKY_SELECTION_START_KEY, startPos)
                    c.putUserData(TMP_STICKY_SELECTION_START_KEY, null)
                    if (startPos != null) c.setSelection(startPos, c.offset)
                }
            }
            override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
                val doesStickyExists = editor.caretModel.allCarets.any { it.getUserData(STICKY_SELECTION_START_KEY) != null }
                return doesStickyExists || myOriginalHandler.isEnabled(editor, caret, dataContext)
            }
        }

        class HandlerToDisable(private val myOriginalHandler: EditorActionHandler) : EditorActionHandler() {
            public override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
                myOriginalHandler.execute(editor, caret, dataContext)
                disableAndRemoveSelection(editor)
            }
            override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
                val doesStickyExists = editor.caretModel.allCarets.any { it.getUserData(STICKY_SELECTION_START_KEY) != null }
                return doesStickyExists || myOriginalHandler.isEnabled(editor, caret, dataContext)
            }
        }

        companion object {
            private val STICKY_SELECTION_START_KEY = Key.create<Int>("StickySelectionHandler.STICKY_SELECTION_START_KEY")
            private val TMP_STICKY_SELECTION_START_KEY = Key.create<Int>("StickySelectionHandler.TMP_STICKY_SELECTION_START_KEY")
            private val HANDLER_REGISTERED_KEY = Key.create<Boolean>("StickySelectionHandler.HANDLER_REGISTERED_KEY")
            private var ourActionsRegistered = false

            private fun toggleStickySelection(editor: Editor) {
                editor.caretModel.runForEachCaret {
                    if (it.getUserData(STICKY_SELECTION_START_KEY) == null) {
                        it.putUserData(STICKY_SELECTION_START_KEY, it.offset)
                    } else {
                        it.putUserData(STICKY_SELECTION_START_KEY, null)
                        it.removeSelection()
                    }
                }
            }

            private fun disableAndRemoveSelection(editor: Editor) {
                editor.caretModel.runForEachCaret {
                    if (it.getUserData(STICKY_SELECTION_START_KEY) != null) {
                        it.putUserData(STICKY_SELECTION_START_KEY, null)
                        it.removeSelection()
                    }
                }
            }
        }
    }
}
