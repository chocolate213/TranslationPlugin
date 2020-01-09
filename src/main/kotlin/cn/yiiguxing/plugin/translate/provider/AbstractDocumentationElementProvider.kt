package cn.yiiguxing.plugin.translate.provider

import cn.yiiguxing.plugin.translate.util.startOffset
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil

abstract class AbstractDocumentationElementProvider<T : PsiComment> : DocumentationElementProvider {

    @Suppress("UNCHECKED_CAST")
    override fun findDocumentationElementAt(psiFile: PsiFile, offset: Int): T? {
        val offsetElement = psiFile.findElementAt(offset) ?: return null
        val comment = PsiTreeUtil.getParentOfType(offsetElement, PsiComment::class.java, false)
        val documentationElement: T? = if (
            comment == null // 如果父元素是注释，则跳过边缘拾取
            && offsetElement is PsiWhiteSpace
            && offsetElement.startOffset == offset // 光标处于边缘处
        ) {
            // 从末尾边缘处拾取
            (offsetElement.prevSibling as? T)?.takeIf { it.isPickAtEdge }
        } else {
            comment as? T
        }

        return documentationElement?.takeIf { it.isDocComment && it.cachedOwner.owner != null }
    }

    /**
     * 检测目标注释是否是文档注释
     */
    protected abstract val T.isDocComment: Boolean

    /**
     * 目标注释是否可在边缘处拾取
     */
    protected open val T.isPickAtEdge: Boolean get() = false

    final override fun getDocumentationOwner(documentationElement: PsiElement): PsiElement? {
        @Suppress("UNCHECKED_CAST")
        return (documentationElement as? T)?.cachedOwner?.owner
    }

    /**
     * 缓存的注释所有者
     */
    @Suppress("MemberVisibilityCanBePrivate")
    protected val T.cachedOwner: CachedOwner
        get() {
            val modificationStamp = containingFile.modificationStamp
            return DOCUMENTATION_OWNER_CACHE[this@cachedOwner]?.takeIf { it.isValid(modificationStamp) }
                ?: CachedOwner(if (isDocComment) documentationOwner else null, modificationStamp).also {
                    DOCUMENTATION_OWNER_CACHE[this@cachedOwner] = it
                }
        }

    /**
     * 文档注释所有者
     */
    protected open val T.documentationOwner: PsiElement? get() = super.getDocumentationOwner(this)

    /**
     * 缓存的注释所有者
     * @property owner 注释所有者
     */
    protected data class CachedOwner(val owner: PsiElement?, val modificationStamp: Long) {
        fun isValid(modificationStamp: Long): Boolean {
            return this.modificationStamp == modificationStamp && owner?.isValid == true
        }
    }

    protected companion object {
        val DOCUMENTATION_OWNER_CACHE = Key.create<CachedOwner?>("DOCUMENTATION_OWNER_CACHE")
    }

}