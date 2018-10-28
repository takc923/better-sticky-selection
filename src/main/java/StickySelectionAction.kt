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
                setSwapHandler(actionManager, IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT, IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION)
                setSwapHandler(actionManager, IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT, IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION)

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
                val visualPos = editor.offsetToVisualPosition(e.offset)
                val caret = editor.caretModel.getCaretAt(visualPos) ?: return
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
                val isInitialPosition = caret.offset == caret.getUserData(STICKY_SELECTION_START_KEY)

                if (isRemoved && !isInitialPosition) {
                    caret.setSelection(e.oldRange.startOffset, e.oldRange.endOffset)
                }
            }
        }

        class SwapHandlerHandler(private val myOriginalHandler: EditorActionHandler, private val myWithSelectionHandler: EditorActionHandler) : EditorActionHandler() {
            public override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
                val isSticky = editor.getUserData(IS_STICKY_SELECTION_KEY) ?: false
                if (isSticky) myWithSelectionHandler.execute(editor, caret, dataContext)
                else myOriginalHandler.execute(editor, caret, dataContext)
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
            private val IS_STICKY_SELECTION_KEY = Key.create<Boolean>("StickySelectionHandler.IS_STICKY_SELECTION_KEY")
            private var ourActionsRegistered = false

            private fun setSwapHandler(actionManager: EditorActionManager, originalHandlerId: String, withSelectionHandlerId: String) {
                val swapHandler = SwapHandlerHandler(actionManager.getActionHandler(originalHandlerId), actionManager.getActionHandler(withSelectionHandlerId))
                actionManager.setActionHandler(originalHandlerId, swapHandler)
            }

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
