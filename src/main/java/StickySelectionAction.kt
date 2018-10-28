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
            val tmpIsSticky = editor.getUserData(IS_STICKY_SELECTION_KEY)
            if (tmpIsSticky == null) {
                editor.caretModel.addCaretListener(MyCaretListener())
                editor.selectionModel.addSelectionListener(MySelectionListener())
                editor.document.addDocumentListener(MyDocumentListener(editor))
            }

            val newIsSticky = !(tmpIsSticky ?: false)
            setStickySelection(editor, newIsSticky)
            if (!newIsSticky) editor.selectionModel.removeSelection(true)
        }

        private class MyDocumentListener(private val editor: Editor) : DocumentListener {
            override fun documentChanged(e: DocumentEvent?) {
                e ?: return
                editor.putUserData(IS_STICKY_SELECTION_KEY, false)
                val caret = editor.caretModel.currentCaret
                caret.putUserData(STICKY_SELECTION_START_KEY, null)
            }
        }

        private class MyCaretListener : CaretListener {
            override fun caretAdded(e: CaretEvent) {
                val caret = e.caret ?: return
                if (e.editor.getUserData(IS_STICKY_SELECTION_KEY) == true) {
                    caret.putUserData(STICKY_SELECTION_START_KEY, caret.offset)
                }
            }

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
        }

        class HandlerToDisable(private val myOriginalHandler: EditorActionHandler) : EditorActionHandler() {
            public override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
                myOriginalHandler.execute(editor, caret, dataContext)
                disableAndRemoveSelection(editor)
            }
        }

        companion object {
            private val STICKY_SELECTION_START_KEY = Key.create<Int>("StickySelectionHandler.STICKY_SELECTION_START_KEY")
            private val TMP_STICKY_SELECTION_START_KEY = Key.create<Int>("StickySelectionHandler.TMP_STICKY_SELECTION_START_KEY")
            private val IS_STICKY_SELECTION_KEY = Key.create<Boolean>("StickySelectionHandler.IS_STICKY_SELECTION_KEY")
            private var ourActionsRegistered = false

            private fun setStickySelection(editor: Editor, enable: Boolean) {
                editor.putUserData(IS_STICKY_SELECTION_KEY, enable)
                editor.caretModel.runForEachCaret {
                    if (enable) it.putUserData(STICKY_SELECTION_START_KEY, it.offset)
                    else it.putUserData(STICKY_SELECTION_START_KEY, null)
                }
            }

            private fun disableAndRemoveSelection(editor: Editor) {
                setStickySelection(editor, false)
                editor.selectionModel.removeSelection(true)
            }
        }
    }
}
